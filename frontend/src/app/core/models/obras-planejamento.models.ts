import { ClasseTensao, OperacaoItemKit, TipoItemKit } from './obras-kit.models';

export type TipoPontoPlanejamento = 'POSTE' | 'TRANSFORMADOR' | 'CHAVE' | 'ESTRUTURA' | 'OUTRO';
export type TipoExecucaoPonto = 'NAO_DEFINIDO' | 'SEM_DESLIGAMENTO' | 'COM_DESLIGAMENTO';
export type OrigemPlanejamento = 'MANUAL' | 'COPILOTO';
export type StatusItemPlanejado = 'PENDENTE' | 'PROGRAMADO' | 'EM_ANDAMENTO' | 'CONCLUIDO' | 'MEDIDO';
export type StatusAnalise = 'PROCESSANDO' | 'CONCLUIDA' | 'FALHOU';

export const TIPOS_PONTO: { value: TipoPontoPlanejamento; label: string }[] = [
  { value: 'POSTE', label: 'Poste' },
  { value: 'TRANSFORMADOR', label: 'Transformador' },
  { value: 'CHAVE', label: 'Chave' },
  { value: 'ESTRUTURA', label: 'Estrutura' },
  { value: 'OUTRO', label: 'Outro' }
];

export interface PontoPlanejamento {
  id: string;
  numero: string;
  pagina: number;
  x: number;
  y: number;
  barramento?: string | null;
  tipo: TipoPontoPlanejamento;
  tipoExecucao: TipoExecucaoPonto;
  temPoda: boolean;
  temAterramento: boolean;
  origem: OrigemPlanejamento;
  levantador?: string | null;
  dataCriacao?: string | null;
  versao: number;
}

export interface VaoPlanejamento {
  id: string;
  idPontoA: string;
  idPontoB: string;
  metragem?: number | null;
  origem: OrigemPlanejamento;
  versao: number;
}

export interface ItemPlanejado {
  id: string;
  tipo: TipoItemKit;
  codigo?: string | null;
  descricao?: string | null;
  operacao: OperacaoItemKit;
  unidade?: string | null;
  quantidadePrevista: number;
  quantidadeExecutada: number;
  status: StatusItemPlanejado;
  valorUnitario?: number | null;
  idProgramacao?: string | null;
  versao: number;
}

export interface KitAplicado {
  id: string;
  idPonto?: string | null;
  idVao?: string | null;
  idKit: string;
  codigo: string;
  descricao: string;
  grupo?: string | null;
  operacao: OperacaoItemKit;
  multiplicador: number;
  executadoAntes: boolean;
  versao: number;
  itens: ItemPlanejado[];
}

export interface AnotacaoPlanejamento {
  id: string;
  pagina: number;
  x: number;
  y: number;
  texto: string;
  autor?: string | null;
  data?: string | null;
  versao: number;
}

export interface IndicadoresPlanejamento {
  percentualExecutado: number;
  valorExecutado: number;
  valorProgramado: number;
  valorPendente: number;
  valorTotal: number;
  servicosSemValor: number;
  pontosSemKit: number;
  vaosSemKit: number;
}

export interface AvancoKit {
  codigo: string;
  descricao: string;
  percentualExecutado: number;
  valorExecutado: number;
  valorTotal: number;
}

export interface PlanejamentoObra {
  idObra: string;
  numeroObra: string;
  tituloObra: string;
  idContrato?: string | null;
  classeTensao?: ClasseTensao | null;
  projetoMestre?: { idAnexo: string; nomeArquivo: string; tamanhoBytes: number } | null;
  rotacoes: Record<string, number>;
  pontos: PontoPlanejamento[];
  vaos: VaoPlanejamento[];
  kits: KitAplicado[];
  anotacoes: AnotacaoPlanejamento[];
  indicadores: IndicadoresPlanejamento;
  avancoKits: AvancoKit[];
  programacoes?: ProgramacaoDoPlano[];
}

/** Programação aberta da obra: selos da árvore e estado atrasado na folha. */
export interface ProgramacaoDoPlano {
  id: string;
  numero: number;
  idEquipe: string;
  codigoEquipe?: string | null;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
  situacao: 'PROGRAMADA' | 'ATRASADA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
}

export interface KitSugerido {
  idKit: string;
  codigo: string;
  descricao: string;
  textoLido: string;
  marcado: boolean;
}

export interface PontoSugerido {
  referencia: string;
  numero: string;
  tipo: TipoPontoPlanejamento;
  pagina: number;
  x: number;
  y: number;
  barramento?: string | null;
  textosLidos: string[];
  confianca: number;
  conflito: boolean;
  kits: KitSugerido[];
}

export interface VaoSugerido {
  referenciaA: string;
  referenciaB: string;
  metragem?: number | null;
  textosLidos: string[];
  confianca: number;
  kits: KitSugerido[];
}

export interface PropostaPlanejamento {
  pontos: PontoSugerido[];
  vaos: VaoSugerido[];
  avisos: string[];
}

export interface AnalisePlanejamento {
  id: string;
  pagina: number;
  status: StatusAnalise;
  mensagem?: string | null;
  proposta?: PropostaPlanejamento | null;
}

export interface DisponibilidadeAnalise {
  disponivel: boolean;
  ultima?: AnalisePlanejamento | null;
}

export interface AceitarProposta {
  pontos: {
    referencia: string;
    numero: string;
    tipo: TipoPontoPlanejamento;
    x: number;
    y: number;
    barramento?: string | null;
    idsKits: string[];
  }[];
  vaos: { referenciaA: string; referenciaB: string; metragem?: number | null; idsKits: string[] }[];
}
