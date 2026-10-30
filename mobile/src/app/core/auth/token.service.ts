import { Injectable, signal, computed } from '@angular/core';
import { jwtDecode } from 'jwt-decode';

export interface JwtPayload {
  sub: string;
  nome: string;
  equipeId?: string;
  estoqueId?: string;
  roles: string[];
  authorities?: string[];
  permissions?: string[];
  tipoUsuario?: string;
  exp: number;
}

@Injectable({
  providedIn: 'root'
})
export class TokenService {
  private readonly STORAGE_KEY = 'ces_auth_token';
  private readonly OWNER_KEY = 'ces_device_user_id';
  private readonly REAUTH_KEY = 'ces_reautenticacao_necessaria';
  private readonly _token = signal<string | null>(localStorage.getItem(this.STORAGE_KEY));
  private readonly _ownerId = signal<string | null>(localStorage.getItem(this.OWNER_KEY));
  private readonly _reautenticacao = signal(localStorage.getItem(this.REAUTH_KEY) === 'true');

  readonly token = computed(() => this._token());

  readonly decodedPayload = computed(() => this.getDecodedPayload());

  readonly equipeId = computed(() => this.decodedPayload()?.equipeId);
  readonly estoqueId = computed(() => this.decodedPayload()?.estoqueId);
  readonly usuarioId = computed(() => this.decodedPayload()?.sub);
  readonly nomeUsuario = computed(() => this.decodedPayload()?.nome);
  readonly ownerId = computed(() => this._ownerId());
  /**
   * O servidor encerrou a sessão (expirou de vez, revogada, usuário desativado). O app
   * continua abrindo e lançando; só o envio espera a pessoa entrar de novo.
   */
  readonly reautenticacaoNecessaria = computed(() => this._reautenticacao());

  constructor() {
    // Preserva a identidade local mesmo após a expiração para proteger dados pendentes.
    const subject = this.getDecodedPayload()?.sub;
    if (!this._ownerId() && subject) {
      localStorage.setItem(this.OWNER_KEY, subject);
      this._ownerId.set(subject);
    }
  }

  setToken(token: string): void {
    const subject = this.decodeToken(token)?.sub;
    if (!subject) throw new Error('O servidor retornou um token sem usuário identificado.');
    localStorage.setItem(this.STORAGE_KEY, token);
    localStorage.setItem(this.OWNER_KEY, subject);
    localStorage.removeItem(this.REAUTH_KEY);
    this._ownerId.set(subject);
    this._reautenticacao.set(false);
    this._token.set(token);
  }

  marcarReautenticacaoNecessaria(): void {
    if (!this._token() || this._reautenticacao()) return;
    localStorage.setItem(this.REAUTH_KEY, 'true');
    this._reautenticacao.set(true);
  }

  /** Relê o armazenamento: outra aba pode ter renovado o token. */
  recarregar(): void {
    this._token.set(localStorage.getItem(this.STORAGE_KEY));
    this._reautenticacao.set(localStorage.getItem(this.REAUTH_KEY) === 'true');
  }

  clearToken(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    localStorage.removeItem(this.REAUTH_KEY);
    this._reautenticacao.set(false);
    this._token.set(null);
  }

  getDecodedPayload(): JwtPayload | null {
    const currentToken = this._token();
    if (!currentToken) return null;

    return this.decodeToken(currentToken);
  }

  decodeToken(token: string): JwtPayload | null {

    try {
      return jwtDecode<JwtPayload>(token);
    } catch {
      return null;
    }
  }

  /**
   * O token de acesso venceu (ou vence nos próximos 30s)? Vencido NÃO significa
   * deslogado: o aparelho renova pelo cookie da sessão quando houver rede.
   */
  isTokenExpired(token: string | null = this._token()): boolean {
    const payload = token ? this.decodeToken(token) : null;
    if (!payload) return true;

    const now = Math.floor(Date.now() / 1000);
    return payload.exp - 30 < now;
  }
}
