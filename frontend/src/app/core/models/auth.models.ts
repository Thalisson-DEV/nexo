export interface LoginRequest {
  login: string;
  senha?: string;
}

export interface TokenResponse {
  /** JWT de acesso, curto. O refresh token só trafega no cookie HttpOnly `ces_sessao`. */
  token: string;
  /** Até quando a sessão do aparelho pode ser renovada sem senha (ISO-8601). */
  sessaoExpiraEm?: string;
}

export interface ChangePasswordRequest {
  senhaAntiga: string;
  novaSenha: string;
}

export interface UserSession {
  id: string;
  login: string;
  nome?: string;
  perfil?: string;
  tipoUsuario?: 'ADMINISTRATIVO' | 'CAMPO';
  token: string;
  permissoes?: string[];
  tema: 'light' | 'dark';
  corPrimaria: string;
  animacoesAtivadas: boolean;
  usarRedesMoveis?: boolean;
  /** Até quando a sessão do aparelho pode ser renovada sem senha (ISO-8601). */
  sessaoExpiraEm?: string;
  /**
   * O servidor encerrou a sessão (expirou, revogada, usuário desativado). O aparelho
   * continua usando o app e guardando lançamentos; só a sincronização espera um novo login.
   */
  reautenticacaoNecessaria?: boolean;
}

/**
 * O que um 401 significa. Só `sessao-invalida` pede novo login; `token` se resolve renovando
 * o token de acesso, e `acesso-negado` é falta de permissão, não de sessão.
 */
export type Motivo401 = 'acesso-negado' | 'sessao-invalida' | 'token';

export function classificar401(corpo: unknown): Motivo401 {
  const problema = (corpo ?? {}) as { type?: string; title?: string; code?: string };
  if (problema.type === 'https://app.exemplo.com/errors/access-denied' || problema.title === 'Acesso Negado') {
    return 'acesso-negado';
  }
  if (problema.code === 'SESSAO_INVALIDA' || problema.type === 'https://app.exemplo.com/errors/sessao-invalida') {
    return 'sessao-invalida';
  }
  return 'token';
}
