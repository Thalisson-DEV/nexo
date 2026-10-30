/**
 * DTOs do módulo de Notificações (caixa de entrada, mural de comunicados e motor de regras).
 *
 * Regra geral: sempre que o backend devolver o par `campo` + `campoRotulo`, a tela exibe o
 * rótulo. Não há tabela de tradução no front — ela sairia de sincronia com o catálogo do
 * servidor.
 */

// ---------------------------------------------------------------------------
// Enums compartilhados
// ---------------------------------------------------------------------------

export type OrigemNotificacao = 'REGRA' | 'COMUNICADO' | 'SISTEMA';

export type CategoriaNotificacao =
  | 'APROVACAO'
  | 'SEGURANCA'
  | 'OPERACIONAL'
  | 'CHAMADO'
  /**
   * Marcações (`@LOGIN`) em texto. Fora de `CHAMADO` de propósito: é o único assunto endereçado
   * à pessoa pelo nome, e quem silenciasse a movimentação da central perderia junto os avisos
   * dirigidos a si.
   */
  | 'MENCAO'
  | 'COMUNICADO'
  | 'SISTEMA';

export type PrioridadeNotificacao = 'BAIXA' | 'NORMAL' | 'ALTA' | 'CRITICA';

// ---------------------------------------------------------------------------
// Caixa de entrada
// ---------------------------------------------------------------------------

export interface NotificacaoResponse {
  id: string;
  origem: OrigemNotificacao;
  /** Agregado de origem (id do desconto, do comunicado...). */
  idReferencia: string | null;
  categoria: CategoriaNotificacao;
  categoriaRotulo: string;
  prioridade: PrioridadeNotificacao;
  prioridadeRotulo: string;
  titulo: string;
  corpo: string;
  /** Rota interna já renderizada pelo servidor. `null` = item não clicável. */
  deeplink: string | null;
  contexto: Record<string, unknown> | null;
  lida: boolean;
  lidaEm: string | null;
  expiraEm: string | null;
  dataCriacao: string;
}

export interface ResumoNotificacoesResponse {
  naoLidas: number;
  /** Só vêm as categorias com contagem maior que zero. */
  naoLidasPorCategoria: Partial<Record<CategoriaNotificacao, number>>;
}

export interface NotificacaoFiltros {
  buscaGlobal?: string;
  categoria?: CategoriaNotificacao;
  prioridade?: PrioridadeNotificacao;
  apenasNaoLidas?: boolean;
  incluirArquivadas?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

// ---------------------------------------------------------------------------
// Preferências
// ---------------------------------------------------------------------------

export interface PreferenciaNotificacaoResponse {
  categoria: CategoriaNotificacao;
  categoriaRotulo: string;
  inApp: boolean;
  email: boolean;
  /**
   * Se a categoria aceita o canal de e-mail. `false` só em `MENCAO`: marcação é aviso interno da
   * aplicação e o servidor recusa o canal mesmo se o payload pedir.
   */
  permiteEmail: boolean;
}

export interface PreferenciaNotificacaoItemRequest {
  categoria: CategoriaNotificacao;
  inApp: boolean;
  email: boolean;
}

export interface PreferenciasNotificacaoRequest {
  preferencias: PreferenciaNotificacaoItemRequest[];
}

// ---------------------------------------------------------------------------
// Mural de comunicados
// ---------------------------------------------------------------------------

export type TipoComunicado = 'DICA' | 'CHANGELOG' | 'AVISO' | 'MANUTENCAO' | 'URGENTE';

export type ApresentacaoComunicado = 'MODAL' | 'BANNER' | 'CARD';

export type StatusComunicado = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO';

export type TipoAlvoSegmento = 'TODOS' | 'PERFIL' | 'USUARIO' | 'TIPO_USUARIO';

export type TipoUsuarioAlvo = 'ADMINISTRATIVO' | 'CAMPO';

export interface AnexoComunicadoResponse {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
}

export interface ComunicadoMuralResponse {
  id: string;
  titulo: string;
  resumo: string | null;
  conteudo: string;
  tipo: TipoComunicado;
  tipoRotulo: string;
  apresentacao: ApresentacaoComunicado;
  /** Link externo (YouTube/Vimeo) — não é anexo. */
  videoUrl: string | null;
  acaoRotulo: string | null;
  acaoDeeplink: string | null;
  fixado: boolean;
  exigeConfirmacao: boolean;
  permiteAdiar: boolean;
  /** Estado deste usuário. */
  visualizado: boolean;
  confirmado: boolean;
  dataInicio: string | null;
  dataFim: string | null;
  anexos: AnexoComunicadoResponse[];
}

export interface AdiarComunicadoRequest {
  /** Opcional: enviar sem corpo deixa a política de 24h a cargo do servidor. */
  adiarAte?: string;
}

// ---------------------------------------------------------------------------
// Administração de comunicados
// ---------------------------------------------------------------------------

export interface SegmentoComunicadoRequest {
  tipoAlvo: TipoAlvoSegmento;
  /** Exigido em PERFIL e USUARIO. */
  idAlvo?: string;
  /** Exigido em TIPO_USUARIO. */
  valorAlvo?: string;
}

export interface SegmentoComunicadoResponse {
  id: string;
  tipoAlvo: TipoAlvoSegmento;
  tipoAlvoRotulo: string;
  idAlvo: string | null;
  valorAlvo: string | null;
  descricaoAlvo?: string | null;
}

export interface ComunicadoRequest {
  titulo: string;
  resumo?: string;
  conteudo: string;
  tipo: TipoComunicado;
  apresentacao: ApresentacaoComunicado;
  videoUrl?: string;
  acaoRotulo?: string;
  acaoDeeplink?: string;
  fixado: boolean;
  exigeConfirmacao: boolean;
  notificarNoSininho: boolean;
  /** Ausente ou `null` vale como `true` — o canal nasce ligado. Só tem efeito na publicação. */
  notificarPorEmail?: boolean;
  prioridadeNotificacao?: PrioridadeNotificacao;
  segmentos: SegmentoComunicadoRequest[];
}

export interface CorrigirTextoComunicadoRequest {
  titulo: string;
  resumo?: string;
  conteudo: string;
}

export interface PublicarComunicadoRequest {
  /** `null` publica agora; data futura agenda. */
  dataInicio?: string | null;
  /** `null` mantém no ar até o arquivamento. */
  dataFim?: string | null;
}

export interface ComunicadoResponse {
  id: string;
  titulo: string;
  resumo: string | null;
  conteudo: string;
  tipo: TipoComunicado;
  tipoRotulo: string;
  apresentacao: ApresentacaoComunicado;
  apresentacaoRotulo: string;
  status: StatusComunicado;
  statusRotulo: string;
  videoUrl: string | null;
  acaoRotulo: string | null;
  acaoDeeplink: string | null;
  dataInicio: string | null;
  dataFim: string | null;
  publicadoEm: string | null;
  fixado: boolean;
  exigeConfirmacao: boolean;
  notificarNoSininho: boolean;
  notificarPorEmail: boolean;
  prioridadeNotificacao: PrioridadeNotificacao | null;
  /** Publicado com início no futuro — não existe status "agendado". */
  agendado: boolean;
  /** Dentro da janela de vigência agora. */
  vigente: boolean;
  segmentos: SegmentoComunicadoResponse[];
  anexos: AnexoComunicadoResponse[];
  dataCriacao: string;
}

export interface ComunicadoFiltros {
  buscaGlobal?: string;
  tipo?: TipoComunicado;
  status?: StatusComunicado;
  exigeConfirmacao?: boolean;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ConfirmacaoComunicadoItem {
  idUsuario: string;
  login: string;
  visualizadoEm: string | null;
  confirmadoEm: string;
}

export interface PendenteComunicadoItem {
  idUsuario: string;
  login: string;
  /** Preenchido significa "abriu e não confirmou". */
  visualizadoEm: string | null;
}

export interface RelatorioConfirmacaoResponse {
  idComunicado: string;
  titulo: string;
  exigeConfirmacao: boolean;
  totalDestinatarios: number;
  totalConfirmados: number;
  totalVisualizados: number;
  confirmados: ConfirmacaoComunicadoItem[];
  pendentes: PendenteComunicadoItem[];
}

// ---------------------------------------------------------------------------
// Motor de regras
// ---------------------------------------------------------------------------

export type ModoGatilho = 'POR_OCORRENCIA' | 'POR_LIMIAR';

export type OperadorCondicao =
  | 'IGUAL'
  | 'DIFERENTE'
  | 'MAIOR_QUE'
  | 'MAIOR_OU_IGUAL'
  | 'MENOR_QUE'
  | 'MENOR_OU_IGUAL'
  | 'CONTEM'
  | 'EM';

export type TipoDestinatarioRegra =
  | 'PERMISSAO'
  | 'PERFIL'
  | 'USUARIO'
  | 'PAPEL_DINAMICO'
  | 'POLITICA';

export interface GatilhoResponse {
  chave: string;
  nome: string;
  descricao: string;
  modo: ModoGatilho;
  modoRotulo: string;
  categoriaPadrao: CategoriaNotificacao;
  /** Usáveis como {{var}} nos templates e como campo das condições. */
  variaveis: string[];
  /** Endereçáveis por destinatário PAPEL_DINAMICO. Vazio em gatilhos POR_LIMIAR. */
  papeis: string[];
}

export interface CondicaoRegraRequest {
  campo: string;
  operador: OperadorCondicao;
  /** Trafega sempre como string, inclusive nos operadores numéricos. */
  valor: string;
}

export interface CondicaoRegraResponse extends CondicaoRegraRequest {
  id: string;
  operadorRotulo: string;
}

export interface DestinatarioRegraRequest {
  tipo: TipoDestinatarioRegra;
  /** Exigido em PERFIL, USUARIO e POLITICA. */
  idAlvo?: string | null;
  /** Exigido em PERMISSAO (nome da permissão) e PAPEL_DINAMICO (papel do gatilho). */
  valorAlvo?: string | null;
}

export interface DestinatarioRegraResponse {
  id: string;
  tipo: TipoDestinatarioRegra;
  tipoRotulo: string;
  idAlvo: string | null;
  valorAlvo: string | null;
  descricaoAlvo?: string | null;
}

export interface RegraNotificacaoRequest {
  /** Imutável depois de criada. */
  chaveGatilho: string;
  nome: string;
  descricao?: string;
  /** `null` assume a categoria padrão do gatilho. */
  categoria?: CategoriaNotificacao;
  prioridade?: PrioridadeNotificacao;
  tituloTemplate: string;
  corpoTemplate: string;
  deeplinkTemplate?: string;
  /** Obrigatório (>= 1) em gatilhos POR_LIMIAR; ignorado em POR_OCORRENCIA. */
  limiar?: number;
  cooldownMinutos?: number;
  /**
   * Canal de e-mail em paralelo ao sininho. Ausente ou `null` vale como `true` — é `Boolean` no
   * backend justamente para distinguir "o administrador desmarcou" de "o cliente não conhece o
   * campo". A categoria `MENCAO` recusa o canal mesmo com `true`.
   */
  enviarEmail?: boolean;
  condicoes?: CondicaoRegraRequest[];
  destinatarios: DestinatarioRegraRequest[];
}

export interface RegraNotificacaoResponse {
  id: string;
  chaveGatilho: string;
  gatilhoNome: string;
  modo: ModoGatilho;
  nome: string;
  descricao: string | null;
  /** Regra de fábrica: editável e desligável, nunca removível. */
  sistema: boolean;
  ativo: boolean;
  categoria: CategoriaNotificacao;
  prioridade: PrioridadeNotificacao;
  tituloTemplate: string;
  corpoTemplate: string;
  deeplinkTemplate: string | null;
  limiar: number | null;
  cooldownMinutos: number;
  /** Estado atual do canal de e-mail. */
  enviarEmail: boolean;
  /** Se a categoria da regra aceita o canal. `false` desabilita o interruptor na tela. */
  permiteEmail: boolean;
  condicoes: CondicaoRegraResponse[];
  destinatarios: DestinatarioRegraResponse[];
  dataCriacao: string;
}

export interface RegraNotificacaoFiltros {
  buscaGlobal?: string;
  chaveGatilho?: string;
  categoria?: CategoriaNotificacao;
  sistema?: boolean;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

// ---------------------------------------------------------------------------
// Políticas reutilizáveis de roteamento
// ---------------------------------------------------------------------------

export interface CondicaoRoteamentoRequest {
  campo: string;
  operador: OperadorCondicao;
  /** Trafega sempre como string, inclusive para booleanos e números. */
  valor: string;
}

export interface RotaRoteamentoRequest {
  ordem: number;
  padrao: boolean;
  usuarioId: string;
  condicoes: CondicaoRoteamentoRequest[];
}

export interface PoliticaRoteamentoRequest {
  /** Imutável depois de criada. */
  chaveGatilho: string;
  nome: string;
  descricao?: string | null;
  /** No PUT, substitui integralmente as rotas e condições anteriores. */
  rotas: RotaRoteamentoRequest[];
}

export interface CondicaoRoteamentoResponse extends CondicaoRoteamentoRequest {
  id: string;
  operadorRotulo: string;
}

export interface RotaRoteamentoResponse {
  id: string;
  ordem: number;
  padrao: boolean;
  usuarioId: string;
  condicoes: CondicaoRoteamentoResponse[];
}

export interface PoliticaRoteamentoResponse {
  id: string;
  chaveGatilho: string;
  nome: string;
  descricao: string | null;
  ativo: boolean;
  rotas: RotaRoteamentoResponse[];
  dataCriacao: string;
}
