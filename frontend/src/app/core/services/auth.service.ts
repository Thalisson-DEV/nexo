import { DestroyRef, Injectable, Injector, inject, signal, computed, effect, untracked } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, from, switchMap, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LoginRequest,
  TokenResponse,
  UserSession,
  ChangePasswordRequest,
  classificar401,
} from '../models/auth.models';
import { LookupCacheService } from '../lookup/services/lookup-cache.service';
import { ViewStateStore } from '../navigation/view-state.store';
import { NavigationHistoryService } from '../navigation/navigation-history.service';
import { ConnectivityService } from './connectivity.service';

/** Margem para renovar antes do vencimento e absorver relógio de aparelho adiantado. */
const MARGEM_VENCIMENTO_MS = 30_000;
/** Nome da trava entre abas: só uma aba renova por vez (Web Locks API). */
const TRAVA_RENOVACAO = 'ces-renovacao-sessao';
/** Logout feito sem rede: a sessão do servidor ainda precisa ser encerrada. */
const LOGOUT_PENDENTE_KEY = 'ces_logout_pendente';
/** A pessoa saiu: nada de restaurar a sessão pelo cookie até um novo login. */
const SAIU_KEY = 'ces_saiu_da_conta';

/** Retornos que valem para qualquer usuário que entrar (não só para a reautenticação). */
const retornoLivre = (retorno: unknown): boolean =>
  typeof retorno === 'string' && retorno.startsWith('/conectar-ia/autorizar?');

/**
 * Sessão do aparelho.
 *
 * <p>Regra central: a sessão local só termina por logout explícito. Token vencido, falta de
 * rede e erro do servidor nunca apagam a sessão. Em campo, sem sinal, o eletricista precisa
 * continuar abrindo o app e lançando; o que não puder ir ao servidor fica na fila.</p>
 *
 * <p>Credenciais: o token de acesso (JWT, ~1h) vive na sessão local e vai em cada requisição.
 * O refresh token vive só no cookie HttpOnly `ces_sessao`, que o JavaScript não lê; renovar é
 * um POST para `/v1/auth/refresh`. Quando o servidor responde que a sessão acabou de verdade
 * (`SESSAO_INVALIDA`), a sessão local é marcada com `reautenticacaoNecessaria`: o app segue
 * funcionando offline e a sincronização aguarda um novo login.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly lookupCache = inject(LookupCacheService);
  private readonly viewState = inject(ViewStateStore);
  private readonly navigationHistory = inject(NavigationHistoryService);
  private readonly injector = inject(Injector);
  private readonly apiUrl = `${environment.apiUrl}/v1/auth`;
  private readonly STORAGE_KEY = 'ces_session_enc';

  // State
  private readonly session = signal<UserSession | null>(this.loadSession());
  private renovacaoEmAndamento: Promise<string> | null = null;

  // Computed
  readonly isAuthenticated = computed(() => !!this.session()?.token);
  readonly currentUser = computed(() => this.session());
  readonly token = computed(() => this.session()?.token);
  readonly reautenticacaoNecessaria = computed(() => !!this.session()?.reautenticacaoNecessaria);

  constructor() {
    if (typeof window === 'undefined') return;
    // Outra aba renovou, entrou ou saiu: esta aba passa a usar o mesmo estado.
    const aoMudarArmazenamento = (evento: StorageEvent) => {
      if (evento.key === this.STORAGE_KEY) this.session.set(this.loadSession());
    };
    const aoVoltarAoApp = () => {
      if (document.visibilityState === 'visible') this.aproveitarConexao();
    };
    window.addEventListener('storage', aoMudarArmazenamento);
    document.addEventListener('visibilitychange', aoVoltarAoApp);
    // Ouvintes globais saem junto com o serviço: um ouvinte órfão consultaria um injetor
    // já destruído (NG0205).
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('storage', aoMudarArmazenamento);
      document.removeEventListener('visibilitychange', aoVoltarAoApp);
    });
    this.solicitarArmazenamentoPersistente();

    // PWA em campo: a conexão aparece por minutos (Wi-Fi da base, um ponto com sinal).
    // Aproveita a janela para encerrar um logout pendente e renovar a sessão, que desliza
    // a validade no servidor, antes de o aparelho voltar para onde não há sinal.
    effect(() => {
      if (this.conectividade()?.isOnline()) untracked(() => this.aproveitarConexao());
    });
  }

  login(credentials: LoginRequest): Observable<TokenResponse> {
    // Um logout pendente tem de terminar antes: a resposta dele apaga o cookie da sessão, e
    // chegando depois do login apagaria o cookie novo.
    return from(this.concluirLogoutPendente())
      .pipe(
        switchMap(() =>
          this.http.post<TokenResponse>(`${this.apiUrl}/login`, credentials, { withCredentials: true }),
        ),
        tap((response) => {
          const anterior = this.session();
          const mesmoUsuario = !!anterior && this.decodeToken(response.token)?.sub === anterior.id;
          localStorage.removeItem(LOGOUT_PENDENTE_KEY);
          localStorage.removeItem(SAIU_KEY);

          if (!mesmoUsuario) {
            // Lookups sao isolados por sessao e nunca devem atravessar a troca de usuario.
            this.lookupCache.clear();
            // Nada de filtros/rascunhos de uma sessão anterior (outro usuário no mesmo navegador).
            this.viewState.clear();
            this.navigationHistory.reset();
          }

          this.aplicarToken(response, credentials.login);
          this.solicitarArmazenamentoPersistente();

          // Reautenticação do mesmo usuário volta para onde ele estava. O pedido de conexão de uma IA
          // volta para a tela de autorização com qualquer usuário: é ele quem decide ali.
          const pedido = this.router.parseUrl(this.router.url).queryParams['retorno'];
          const retorno = mesmoUsuario || retornoLivre(pedido) ? pedido : null;
          this.router.navigateByUrl(typeof retorno === 'string' && retorno.startsWith('/') ? retorno : '/home');
        }),
        catchError((error) => {
          console.error('Login failed', error);
          return throwError(() => error);
        }),
      );
  }

  /**
   * Logout explícito: o único caminho que apaga a sessão local. Sem rede, a sessão do
   * servidor é encerrada assim que o aparelho voltar a ter conexão.
   */
  logout(): void {
    localStorage.setItem(SAIU_KEY, 'true');
    localStorage.setItem(LOGOUT_PENDENTE_KEY, 'true');
    void this.concluirLogoutPendente();

    this.lookupCache.clear();
    localStorage.removeItem(this.STORAGE_KEY);
    this.session.set(null);
    this.viewState.clear();
    this.navigationHistory.reset();
    this.router.navigate(['/login']);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/alterar-senha`, request);
  }

  updateUserSession(data: Partial<UserSession>): void {
    const current = this.session();
    if (current) {
      const updated = { ...current, ...data };
      this.saveSession(updated);
      this.session.set(updated);
    }
  }

  /** O token de acesso atual já venceu (ou vence nos próximos segundos)? */
  tokenVencido(token = this.token()): boolean {
    if (!token) return true;
    const exp = this.decodeToken(token)?.exp;
    return typeof exp === 'number' && exp * 1000 - MARGEM_VENCIMENTO_MS <= Date.now();
  }

  /**
   * Token pronto para uso: renova antes se já venceu e houver rede. Sem rede, ou se a
   * renovação falhar, devolve o token atual e deixa a requisição seguir o fluxo normal.
   */
  async tokenAtualizado(): Promise<string | null> {
    const atual = this.token() ?? null;
    if (!atual || !this.tokenVencido(atual) || !this.podeRenovar()) return atual;
    try {
      return await this.renovarToken(atual);
    } catch {
      return atual;
    }
  }

  podeRenovar(): boolean {
    return !this.reautenticacaoNecessaria() && this.online();
  }

  /**
   * Conectividade efetiva do app, não só a do aparelho: com dados móveis bloqueados para
   * a equipe de campo, a renovação também não sai pelo 4G. Resolvida sob demanda porque o
   * `ConnectivityService` depende deste serviço.
   */
  private online(): boolean {
    const conectividade = this.conectividade();
    if (conectividade) return conectividade.isOnline();
    return typeof navigator === 'undefined' || navigator.onLine;
  }

  private conectividade(): ConnectivityService | null {
    try {
      return this.injector.get(ConnectivityService, null);
    } catch {
      return null; // injetor já destruído
    }
  }

  private aproveitarConexao(): void {
    if (!this.online()) return;
    void this.concluirLogoutPendente();
    const token = this.token();
    if (token && this.tokenVencido(token) && this.podeRenovar()) {
      this.renovarToken(token).catch(() => undefined);
    }
  }

  /**
   * Renova o token de acesso pelo cookie da sessão.
   *
   * <p>Uma renovação por vez, mesmo entre abas: o refresh token gira a cada uso, e duas
   * renovações simultâneas com o mesmo cookie fariam a segunda chegar com um token já
   * substituído. Dentro da trava, se outra aba já renovou, usa o token dela.</p>
   *
   * @param tokenRecusado o token que o servidor acabou de recusar; outro token válido na
   *        sessão significa que alguém já renovou.
   */
  renovarToken(tokenRecusado?: string | null): Promise<string> {
    if (!this.renovacaoEmAndamento) {
      this.renovacaoEmAndamento = this.comTravaEntreAbas(() => this.executarRenovacao(tokenRecusado)).finally(
        () => (this.renovacaoEmAndamento = null),
      );
    }
    return this.renovacaoEmAndamento;
  }

  /**
   * Tenta recuperar a sessão pelo cookie quando a sessão local sumiu (armazenamento do
   * navegador limpo pelo sistema). Não roda depois de um logout explícito.
   */
  async restaurarSessao(): Promise<boolean> {
    if (this.session() || localStorage.getItem(SAIU_KEY)) return false;
    if (!this.online()) return false;
    // Sinal fraco não pode prender a tela de login: depois do prazo, mostra o formulário.
    const prazo = new Promise<never>((_, rejeitar) => setTimeout(() => rejeitar(new Error('prazo')), 5000));
    try {
      await Promise.race([this.renovarToken(null), prazo]);
      return !!this.session();
    } catch {
      return false;
    }
  }

  /** Um 401 chegou por fora do HttpClient (fetch em streaming): decide o que fazer. */
  tratarNaoAutorizado(corpo: unknown): void {
    const motivo = classificar401(corpo);
    if (motivo === 'sessao-invalida') {
      this.marcarReautenticacaoNecessaria();
    } else if (motivo === 'token' && this.podeRenovar()) {
      this.renovarToken(this.token()).catch(() => undefined);
    }
  }

  /** O servidor encerrou a sessão. O app continua; a sincronização espera novo login. */
  marcarReautenticacaoNecessaria(): void {
    if (this.session() && !this.reautenticacaoNecessaria()) {
      this.updateUserSession({ reautenticacaoNecessaria: true });
    }
  }

  private async executarRenovacao(tokenRecusado?: string | null): Promise<string> {
    const armazenada = this.loadSession();
    if (armazenada?.token && armazenada.token !== tokenRecusado && !this.tokenVencido(armazenada.token)) {
      this.session.set(armazenada);
      return armazenada.token;
    }

    try {
      const resposta = await firstValueFrom(
        this.http.post<TokenResponse>(`${this.apiUrl}/refresh`, null, { withCredentials: true }),
      );
      this.aplicarToken(resposta);
      return resposta.token;
    } catch (erro) {
      if (erro instanceof HttpErrorResponse && erro.status === 401) {
        this.marcarReautenticacaoNecessaria();
      }
      throw erro;
    }
  }

  private async comTravaEntreAbas<T>(acao: () => Promise<T>): Promise<T> {
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    return locks ? locks.request(TRAVA_RENOVACAO, acao) : acao();
  }

  /** Monta (login) ou atualiza (renovação) a sessão local a partir de um token novo. */
  private aplicarToken(response: TokenResponse, login?: string): void {
    const decoded = this.decodeToken(response.token);
    const atual = this.session();
    // Cookie de outro usuário não substitui a sessão deste aparelho em silêncio.
    if (!login && atual && decoded?.sub !== atual.id) {
      this.marcarReautenticacaoNecessaria();
      throw new Error('Sessão renovada pertence a outro usuário');
    }

    const tipoUsuario: 'ADMINISTRATIVO' | 'CAMPO' =
      decoded?.tipoUsuario === 'CAMPO' ? 'CAMPO' : 'ADMINISTRATIVO';
    const savedThemeMode = localStorage.getItem('ces_theme_mode');
    const savedColor = localStorage.getItem('ces_primary_color');
    const savedRipple = localStorage.getItem('ces_ripple_enabled');
    const savedRedesMoveis = localStorage.getItem('ces_usar_redes_moveis');
    const usarRedesMoveis =
      savedRedesMoveis !== null ? savedRedesMoveis === 'true' : tipoUsuario !== 'CAMPO';

    const userSession: UserSession = {
      ...atual,
      id: decoded?.sub || '',
      login: login ?? atual?.login ?? '',
      tipoUsuario,
      usarRedesMoveis: atual?.usarRedesMoveis ?? usarRedesMoveis,
      token: response.token,
      permissoes: decoded?.roles || [],
      sessaoExpiraEm: response.sessaoExpiraEm,
      reautenticacaoNecessaria: false,
      tema:
        savedThemeMode === 'dark' || savedThemeMode === 'light'
          ? savedThemeMode
          : atual?.tema || decoded?.preferencias?.tema || 'light',
      corPrimaria: (savedColor as any) || atual?.corPrimaria || decoded?.preferencias?.corPrimaria || 'blue',
      animacoesAtivadas:
        savedRipple !== null
          ? savedRipple !== 'false'
          : (atual?.animacoesAtivadas ?? decoded?.preferencias?.animacoesAtivadas ?? true),
    };

    this.saveSession(userSession);
    this.session.set(userSession);
  }

  private async concluirLogoutPendente(): Promise<void> {
    if (!localStorage.getItem(LOGOUT_PENDENTE_KEY)) return;
    if (!this.online()) return;
    try {
      await firstValueFrom(this.http.post<void>(`${this.apiUrl}/logout`, null, { withCredentials: true }));
      localStorage.removeItem(LOGOUT_PENDENTE_KEY);
    } catch {
      // Sem resposta: o próximo evento online tenta de novo.
    }
  }

  /**
   * Pede ao navegador que não apague os dados do app quando faltar espaço. Sem isso o
   * Android pode limpar a sessão e a fila offline de um aparelho com a memória cheia.
   */
  private solicitarArmazenamentoPersistente(): void {
    const storage = typeof navigator !== 'undefined' ? navigator.storage : undefined;
    if (!storage?.persist || !this.session()) return;
    storage
      .persisted()
      .then((persistente) => (persistente ? true : storage.persist()))
      .catch(() => undefined);
  }

  /**
   * Decodifica o payload do JWT sem bibliotecas externas
   */
  private decodeToken(token: string): any {
    try {
      const payload = token.split('.')[1];
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join(''),
      );
      return JSON.parse(jsonPayload);
    } catch (e) {
      console.error('Error decoding token', e);
      return null;
    }
  }

  /**
   * Ofuscação leve (base64 + reverse) para não deixar a sessão em texto plano no
   * LocalStorage. Não é criptografia: o refresh token, que é o segredo longo, fica no
   * cookie HttpOnly e nunca passa por aqui.
   */
  private saveSession(session: UserSession): void {
    try {
      const plainText = JSON.stringify(session);
      const encrypted = btoa(encodeURIComponent(plainText).split('').reverse().join(''));
      localStorage.setItem(this.STORAGE_KEY, encrypted);
    } catch (e) {
      console.error('Error saving session', e);
    }
  }

  private loadSession(): UserSession | null {
    try {
      const encrypted = localStorage.getItem(this.STORAGE_KEY);
      if (!encrypted) return null;

      const decrypted = decodeURIComponent(atob(encrypted).split('').reverse().join(''));
      return JSON.parse(decrypted) as UserSession;
    } catch (e) {
      // Conteúdo ilegível não é sessão; mas não apaga: pode ser escrita parcial de outra aba.
      return null;
    }
  }
}
