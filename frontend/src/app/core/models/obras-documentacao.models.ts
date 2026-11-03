export type FinalidadeChecklist = 'VISITA_PREVIA' | 'AS_BUILT';
export type TipoPerguntaChecklist = 'ESCOLHA_UNICA' | 'MULTIPLA_ESCOLHA' | 'TEXTO' | 'NUMERO' | 'DATA';
export type TipoEquipamento = 'RELIGADOR' | 'REGULADOR';
export type StatusDocumentoAsBuilt = 'PROCESSANDO' | 'CONCLUIDO' | 'ERRO';

export interface PerguntaChecklist {
  id: string;
  enunciado: string;
  tipo: TipoPerguntaChecklist;
  obrigatoria: boolean;
  aceitaNaoSeAplica: boolean;
  opcoes: string[] | null;
}

export interface GrupoChecklist {
  titulo: string;
  perguntas: PerguntaChecklist[];
}

export interface ItemEvidenciaModelo {
  id: string;
  titulo: string;
  obrigatoria: boolean;
}

export interface EstruturaChecklist {
  grupos: GrupoChecklist[];
  /** Fotos que o book pede em cada ponto; só no modelo de ficha As Built. */
  evidencias?: ItemEvidenciaModelo[];
}

export interface ModeloChecklistRequest {
  finalidade: FinalidadeChecklist;
  titulo: string;
  descricao: string | null;
  estrutura: EstruturaChecklist;
}

export interface ModeloChecklistResponse {
  id: string;
  finalidade: FinalidadeChecklist;
  titulo: string;
  descricao: string | null;
  estrutura: EstruturaChecklist;
  grupos: number;
  perguntas: number;
  emUso: boolean;
  ativo: boolean;
}

export interface ModeloChecklistFilter {
  buscaGlobal?: string | null;
  finalidade?: FinalidadeChecklist | null;
  ativo?: boolean | null;
  page?: number;
  size?: number;
  sort?: string | string[];
}

export interface RespostaInformada {
  idPergunta: string;
  valor: string | null;
  selecionadas: string[];
  naoSeAplica: boolean;
}

export interface ItemResposta extends RespostaInformada {
  grupo: string;
  enunciado: string;
  tipo: TipoPerguntaChecklist;
  obrigatoria: boolean;
}

export interface RespostasChecklist {
  itens: ItemResposta[];
}

export interface VisitaPrevia {
  id: string;
  idModelo: string;
  tituloModelo: string;
  dataVisita: string;
  responsavel: string;
  observacoes: string | null;
  respostas: RespostasChecklist;
  usuario: string | null;
  data: string;
}

export interface VisitaPreviaRequest {
  idModelo: string;
  dataVisita: string;
  responsavel: string;
  observacoes: string | null;
  respostas: RespostaInformada[];
}

export interface FichaAsBuilt {
  id: string;
  idModelo: string;
  tituloModelo: string;
  completa: boolean;
  pendencias: string[];
  respostas: RespostasChecklist;
  usuario: string | null;
  data: string;
}

export interface EvidenciaPonto {
  id: string;
  /** Sem item, é uma foto avulsa do ponto. */
  idItem: string | null;
  tituloItem: string | null;
  legenda: string | null;
  nomeArquivo: string;
  usuario: string | null;
  data: string;
}

export interface PontoDocumentacao {
  id: string;
  numero: string;
  pagina: number;
  tipo: string;
  executado: boolean;
  ficha: FichaAsBuilt | null;
  /** O da ficha ou, antes dela, o das fotos já lançadas. */
  idModelo: string | null;
  tituloModelo: string | null;
  itensEvidencia: ItemEvidenciaModelo[];
  evidencias: EvidenciaPonto[];
}

export interface DocumentoAsBuilt {
  id: string;
  numeroVersao: number;
  status: StatusDocumentoAsBuilt;
  idAnexo: string | null;
  mensagemErro: string | null;
  usuario: string | null;
  data: string;
}

export interface EquipamentoObra {
  id: string;
  idPonto: string;
  numeroPonto: string;
  pagina: number;
  tipo: TipoEquipamento;
  trafoAuxiliar: boolean;
  observacao: string | null;
}

export interface EquipamentoRequest {
  tipo: TipoEquipamento;
  trafoAuxiliar: boolean;
  observacao: string | null;
}

export interface DocumentacaoObra {
  visitaRealizada: boolean;
  visitas: VisitaPrevia[];
  temProjetoMestre: boolean;
  pontos: PontoDocumentacao[];
  asBuilt: { pontosExecutados: number; fichasCompletas: number; pontosPendentes: string[]; pontosSemEvidencia: string[] };
  documentos: DocumentoAsBuilt[];
  equipamentos: EquipamentoObra[];
}
