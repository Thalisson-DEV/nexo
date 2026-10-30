import { DestroyRef, Injectable, Injector, inject, computed, effect, untracked } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, from, map, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenService } from './token.service';
import { DbService } from '../db/db.service';
import { SessaoService } from '../sessao/sessao.service';
import { RascunhoService } from '../sessao/rascunho.service';
import { rotaInternaSegura } from '../sessao/snapshot';
import { ConnectivityService } from '../sync/connectivity.service';

/** Uma aba renova por vez: o refresh token gira a cada uso. */
const TRAVA_RENOVACAO = 'ces-renovacao-sessao';
/** Logout feito sem rede: a sessão no servidor ainda precisa ser encerrada. */
const LOGOUT_PENDENTE_KEY = 'ces_logout_pendente';
/** A pessoa tocou em "Sair": nada de restaurar a sessão pelo cookie até um novo login. */
const SAIU_KEY = 'ces_saiu_da_conta';

/**
 * O que um 401 significa. Só `sessao-invalida` pede novo login; `token` se resolve
 * renovando o token de acesso pelo cookie da sessão.
 */
export function classificar401(corpo: unknown): 'sessao-invalida' | 'token' {
  const problema = (corpo ?? {}) as { type?: string; code?: string };
  return problema.code === 'SESSAO_INVALIDA' || problema.type === 'https://app.exemplo.com/errors/sessao-invalida'
    ? 'sessao-invalida'
    : 'token';
}

export interface LoginRequest {
  login: string;
  senha: string;
}

export interface TokenResponse {
  /** JWT de acesso (~1h). O refresh token só trafega no cookie HttpOnly `ces_sessao`. */
  token: string;
  sessaoExpiraEm?: string;
}

export interface UsuarioLogado {
  id: string;
  nome: string;
  equipeId?: string;
  roles: string[];
  tipoUsuario?: string;
}

/**
 * Sessão do aparelho de campo.
 *
 * <p>Regra central: quem entrou continua dentro até tocar em "Sair". Token vencido, falta
 * de internet (inclusive 4G bloqueado ou Wi-Fi sem saída) e erro do servidor nunca mandam
 * para o login: o encarregado abre o app e lança sem sinal, e o envio acontece quando der.
 * O token de acesso é renovado sozinho pelo cookie HttpOnly da sessão; se o servidor disser
 * que a sessão acabou (`SESSAO_INVALIDA`), o app marca `reautenticacaoNecessaria`, segue
 * funcionando e mostra um aviso para entrar de novo quando houver internet.</p>
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);
  private readonly dbService = inject(DbService);
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly injector = inject(Injector);

  private renovacaoEmAndamento: Promise<string> | null = null;
  private logoutEmAndamento: Promise<void> | null = null;

  readonly reautenticacaoNecessaria = this.tokenService.reautenticacaoNecessaria;

  constructor() {
    if (typeof window === 'undefined') return;
    // Outra aba renovou ou saiu: esta passa a usar o mesmo token.
    const aoMudarArmazenamento = (evento: StorageEvent) => {
      if (evento.key === 'ces_auth_token' || evento.key === 'ces_reautenticacao_necessaria') {
        this.tokenService.recarregar();
      }
    };
    const aoVoltarAoApp = () => {
      if (document.visibilityState === 'visible') this.aproveitarConexao();
    };
    window.addEventListener('storage', aoMudarArmazenamento);
    document.addEventListener('visibilitychange', aoVoltarAoApp);
    // Ouvintes globais saem junto com o serviço (um órfão consultaria injetor destruído).
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('storage', aoMudarArmazenamento);
      document.removeEventListener('visibilitychange', aoVoltarAoApp);
    });
    // A conexão em campo aparece por minutos (Wi-Fi da base, um ponto com sinal): aproveita
    // para renovar a sessão, que desliza a validade no servidor, e concluir logout pendente.
    effect(() => {
      if (this.conectividade()?.isOnline()) untracked(() => this.aproveitarConexao());
    });
    this.solicitarArmazenamentoPersistente();
  }

  /**
   * Existe sessão neste aparelho. Não depende do token estar dentro da validade nem de
   * haver rede: é o que decide se o app abre direto ou pede login.
   */
  isAuthenticated(): boolean {
    return !!this.tokenService.token();
  }

  /** Mantido por compatibilidade: com sessão local o app sempre abre, com ou sem rede. */
  canUseOffline(): boolean {
    const identity = this.tokenService.getDecodedPayload()?.sub;
    return !!identity && identity === this.tokenService.ownerId();
  }

  /** Pode tentar renovar agora? Respeita o bloqueio de dados móveis da equipe de campo. */
  podeRenovar(): boolean {
    return !!this.tokenService.token() && !this.tokenService.reautenticacaoNecessaria() && this.online();
  }

  /**
   * Token pronto para enviar: renova antes se venceu e houver rede. Sem rede ou se a
   * renovação falhar, devolve o atual e a requisição segue o fluxo normal (fila).
   */
  async tokenAtualizado(): Promise<string | null> {
    const atual = this.tokenService.token();
    if (!atual || !this.tokenService.isTokenExpired(atual) || !this.podeRenovar()) return atual;
    try {
      return await this.renovarToken(atual);
    } catch {
      return atual;
    }
  }

  /**
   * Renova o token de acesso pelo cookie da sessão. Uma renovação por vez, inclusive entre
   * abas; dentro da trava, se outra aba já renovou, usa o token dela.
   */
  renovarToken(tokenRecusado?: string | null): Promise<string> {
    if (!this.renovacaoEmAndamento) {
      const executar = () => this.executarRenovacao(tokenRecusado);
      const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
      const promessa = (locks?.request ? locks.request(TRAVA_RENOVACAO, executar) : executar()) as Promise<string>;
      this.renovacaoEmAndamento = promessa.finally(() => (this.renovacaoEmAndamento = null));
    }
    return this.renovacaoEmAndamento;
  }

  /**
   * Recupera a sessão pelo cookie quando o token local sumiu (o sistema limpou os dados
   * do navegador). Nunca depois de um "Sair". Desiste em 5s para não prender o login.
   */
  async restaurarSessao(): Promise<boolean> {
    if (this.tokenService.token() || localStorage.getItem(SAIU_KEY) || !this.online()) return false;
    const prazo = new Promise<never>((_, rejeitar) => setTimeout(() => rejeitar(new Error('prazo')), 5000));
    try {
      await Promise.race([this.renovarToken(null), prazo]);
      return !!this.tokenService.token();
    } catch {
      return false;
    }
  }

  private async executarRenovacao(tokenRecusado?: string | null): Promise<string> {
    this.tokenService.recarregar();
    const armazenado = this.tokenService.token();
    if (armazenado && armazenado !== tokenRecusado && !this.tokenService.isTokenExpired(armazenado)) {
      return armazenado;
    }
    try {
      const resposta = await firstValueFrom(
        this.http.post<TokenResponse>(`${environment.apiBaseUrl}/v1/auth/refresh`, null, { withCredentials: true })
      );
      const novoUsuario = this.tokenService.decodeToken(resposta.token)?.sub;
      const dono = this.tokenService.ownerId();
      if (!novoUsuario || (dono && novoUsuario !== dono)) {
        // Cookie de outra conta não troca o dono dos dados deste aparelho em silêncio.
        this.tokenService.marcarReautenticacaoNecessaria();
        throw new Error('A sessão renovada pertence a outra conta.');
      }
      this.tokenService.setToken(resposta.token);
      return resposta.token;
    } catch (erro) {
      if (erro instanceof HttpErrorResponse && erro.status === 401) {
        this.tokenService.marcarReautenticacaoNecessaria();
      }
      throw erro;
    }
  }

  private online(): boolean {
    const conectividade = this.conectividade();
    if (conectividade) return conectividade.isOnline();
    return typeof navigator === 'undefined' || navigator.onLine;
  }

  /** Resolvido sob demanda: o ConnectivityService depende, indiretamente, deste serviço. */
  private conectividade(): ConnectivityService | null {
    try {
      return this.injector.get(ConnectivityService, null);
    } catch {
      return null;
    }
  }

  private aproveitarConexao(): void {
    if (!this.online()) return;
    void this.concluirLogoutPendente();
    const token = this.tokenService.token();
    if (token && this.tokenService.isTokenExpired(token) && this.podeRenovar()) {
      this.renovarToken(token).catch(() => undefined);
    }
  }

  private concluirLogoutPendente(): Promise<void> {
    if (!this.logoutEmAndamento) {
      this.logoutEmAndamento = this.enviarLogoutPendente().finally(() => (this.logoutEmAndamento = null));
    }
    return this.logoutEmAndamento;
  }

  private async enviarLogoutPendente(): Promise<void> {
    if (!localStorage.getItem(LOGOUT_PENDENTE_KEY) || !this.online()) return;
    try {
      await firstValueFrom(
        this.http.post<void>(`${environment.apiBaseUrl}/v1/auth/logout`, null, { withCredentials: true })
      );
      localStorage.removeItem(LOGOUT_PENDENTE_KEY);
    } catch {
      // Sem resposta: a próxima conexão tenta de novo.
    }
  }

  /** Pede ao Android que não apague sessão e fila do app quando faltar espaço. */
  private solicitarArmazenamentoPersistente(): void {
    const storage = typeof navigator !== 'undefined' ? navigator.storage : undefined;
    if (!storage?.persist || !this.tokenService.token()) return;
    storage.persisted().then(ok => (ok ? true : storage.persist())).catch(() => undefined);
  }

  readonly hasLocalIdentity = computed(() => !!this.tokenService.ownerId());

  readonly currentUser = computed<UsuarioLogado | null>(() => {
    const payload = this.tokenService.getDecodedPayload();
    if (!payload) return null;

    return {
      id: payload.sub,
      nome: payload.nome,
      equipeId: payload.equipeId,
      roles: payload.roles || [],
      tipoUsuario: payload.tipoUsuario
    };
  });

  readonly isAdministrativo = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    return user.tipoUsuario === 'ADMINISTRATIVO';
  });

  readonly isCampo = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    return user.tipoUsuario === 'CAMPO';
  });

  /**
   * Módulos exclusivos do perfil administrativo.
   *
   * O encarregado de campo não opera inspeções de segurança nem desvios nem descontos —
   * esses módulos não aparecem na navegação, não são sincronizados e as rotas
   * ficam bloqueadas. Centralizado aqui para que navegação, rotas, dashboard e
   * SyncService não divirjam.
   */
  readonly podeAcessarInspecoes = computed(() => this.isAdministrativo());
  readonly podeAcessarDescontos = computed(() => this.isAdministrativo());

  /**
   * Módulos de logística e comercial (materiais, estoques, saldos, solicitações,
   * transferências, ações e execuções comerciais).
   *
   * O perfil administrativo opera apenas inspeções e desvios: os endpoints de
   * material respondem 403 para ele, então sincronizá-los só produzia etapas
   * falhas e um alerta de "dados não carregados" logo após o login. Tudo que
   * depende de material passa por aqui — navegação, rotas e SyncService.
   */
  readonly podeAcessarLogistica = computed(() => !this.isAdministrativo());
  /**
   * Almoxarifado do perfil administrativo (gestão de solicitações comerciais e movimentações
   * de estoque), separado da logística do encarregado. Cada parte depende da permissão que o
   * backend exige no endpoint: navegação, rotas e botões não oferecem o que daria 403.
   */
  readonly podeVerSolicitacoesAlmox = computed(() => this.isAdministrativo() && this.temPermissao('estoque:solicitacao:read'));
  readonly podeGerirSolicitacoesAlmox = computed(() => this.podeVerSolicitacoesAlmox() && this.temPermissao('estoque:solicitacao:write'));
  readonly podeVerMovimentacoes = computed(() => this.isAdministrativo() && this.temPermissao('estoque:movimentacao:read'));
  readonly podeMovimentarEstoque = computed(() => this.isAdministrativo() && this.temPermissao('estoque:movimentacao:write') && this.temPermissao('estoque:saldo:read'));
  readonly podeAcessarAlmoxarifado = computed(() => this.podeVerSolicitacoesAlmox() || this.podeVerMovimentacoes());
  readonly podeConsultarDesvios = computed(() => this.isAdministrativo());
  readonly podeTratarDesvios = computed(() => this.isAdministrativo());

  temPermissao(permissao: string): boolean {
    const payload = this.tokenService.getDecodedPayload();
    if (!payload) return false;
    return [
      ...(payload.roles ?? []),
      ...(payload.authorities ?? []),
      ...(payload.permissions ?? []),
    ].includes(permissao);
  }

  login(credentials: LoginRequest): Observable<TokenResponse> {
    // Um logout pendente termina antes: a resposta dele apaga o cookie, e chegando depois
    // do login apagaria o cookie da sessão nova.
    return from(this.concluirLogoutPendente()).pipe(
      switchMap(() => this.http.post<TokenResponse>(
        `${environment.apiBaseUrl}/v1/auth/login`, credentials, { withCredentials: true })),
      switchMap(response => {
        const nextUserId = this.tokenService.decodeToken(response.token)?.sub;
        if (!nextUserId) throw new Error('O servidor retornou um token sem usuário identificado.');
        return from(this.dbService.prepareForLogin(nextUserId, this.tokenService.ownerId())).pipe(
          map(() => {
            localStorage.removeItem(LOGOUT_PENDENTE_KEY);
            localStorage.removeItem(SAIU_KEY);
            this.tokenService.setToken(response.token);
            this.solicitarArmazenamentoPersistente();
            return response;
          })
        );
      })
    );
  }

  /**
   * O servidor encerrou a sessão. NÃO tira a pessoa da tela: em campo, sem sinal, o login
   * não teria como funcionar e o lançamento em andamento se perderia. Marca a sessão; o
   * aviso do shell oferece "Entrar novamente" quando houver internet.
   */
  requireReauthentication(): void {
    this.tokenService.marcarReautenticacaoNecessaria();
  }

  /**
   * Vai para o login da mesma conta, a pedido da pessoa. O formulário aberto é gravado
   * antes, e o login devolve exatamente para cá (`volta`).
   */
  entrarNovamente(): void {
    if (this.router.url.startsWith('/login')) return;
    const volta = rotaInternaSegura(this.router.url);
    void this.rascunhos.gravarTodos();
    void this.sessao.gravarAgora();
    void this.router.navigate(['/login'], volta ? { queryParams: { volta } } : {});
  }

  logout(): void {
    localStorage.setItem(SAIU_KEY, 'true');
    localStorage.setItem(LOGOUT_PENDENTE_KEY, 'true');
    void this.concluirLogoutPendente();
    this.tokenService.clearToken();
    // Sair é intencional: a próxima abertura não volta para dentro da conta.
    // Rascunhos não enviados ficam (só saem com confirmação explícita na tela de Conta).
    void this.sessao.esquecer();
    this.router.navigate(['/login']);
  }
}
