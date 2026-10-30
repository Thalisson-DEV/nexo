/** Conexões do Nexo com IAs externas (Claude, ChatGPT, Cursor...) pelo protocolo MCP. */

export type TipoConexaoIa = 'OAUTH' | 'CHAVE';
export type SituacaoConexaoIa = 'CONECTADA' | 'VENCIDA' | 'DESCONECTADA';

export interface ConexaoIaInfo {
  /** Endereço que a pessoa cola na IA. */
  urlServidor: string;
  /** O perfil tem alguma ferramenta que cadastra ou altera registros. */
  podeEscrever: boolean;
  ferramentas: number;
  ferramentasEscrita: number;
}

export interface ConexaoIa {
  id: string;
  nome: string;
  tipo: TipoConexaoIa;
  permiteEscrita: boolean;
  situacao: SituacaoConexaoIa;
  prefixoChave: string | null;
  aplicativo: string | null;
  criadaEm: string;
  ultimoUsoEm: string | null;
  expiraEm: string | null;
  desconectadaEm: string | null;
  totalChamadas: number;
  usuarioId: string;
  /** Só na visão do administrador. */
  usuarioLogin: string | null;
}

export interface CriarChaveIaRequest {
  nome: string;
  permiteEscrita: boolean;
  validadeDias: number;
}

export interface ChaveIaCriada {
  conexao: ConexaoIa;
  /** Mostrada uma única vez. */
  chave: string;
}

/** Parâmetros do pedido de autorização que a IA manda pela URL. */
export interface AutorizarIaRequest {
  responseType: string;
  clientId: string;
  redirectUri: string;
  codeChallenge?: string | null;
  codeChallengeMethod?: string | null;
  state?: string | null;
  scope?: string | null;
  resource?: string | null;
  permiteEscrita: boolean;
}

export interface AutorizacaoIaPrevia {
  aplicativo: string;
  site: string | null;
  destino: string;
  podeEscrever: boolean;
}

export interface AutorizacaoIaRedirecionamento {
  redirectUrl: string;
}
